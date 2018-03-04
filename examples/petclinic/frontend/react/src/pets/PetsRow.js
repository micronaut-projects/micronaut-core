import React from 'react'
import PetsCell from "./PetsCell";
import {array} from "prop-types";

const PetsRow = ({pets}) => <div className="row">{pets.map((pet, i) => <PetsCell key={i} pet={pet} />)}</div>

PetsRow.propTypes = {pets: array}

export default PetsRow